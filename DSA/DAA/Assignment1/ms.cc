#include <iostream>
#include <vector>

using namespace std;



void merge(vector<int>& arr, int left, int mid, int right) {
    int n1 = mid - left + 1;
    int n2 = right - mid;

    vector<int> L(n1), R(n2);

    for (int i = 0; i < n1; i++)
        L[i] = arr[left + i];
    for (int j = 0; j < n2; j++)
        R[j] = arr[mid + 1 + j];

    int i = 0, j = 0, k = left;

    while (i < n1 && j < n2) {
        if (L[i] <= R[j])
            arr[k++] = L[i++];
        else
            arr[k++] = R[j++];
    }

    while (i < n1)
        arr[k++] = L[i++];

    while (j < n2)
        arr[k++] = R[j++];
}

void mSort(vector<int>& nums,int s,int e){
    if(s < e){
        int q = floor((s+e)/2);
        mSort(nums,s,q);
        mSort(nums,q+1,e);
        merge(nums,s,q,e);
    }
}

int main(){
    vector<int> nums = {12, 11, 13, 5, 6, 7 };
    mSort(nums,0,nums.size());

    for(int i : nums){
        cout<<i<<" ";
    }
}